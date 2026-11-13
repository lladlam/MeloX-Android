// Verifies that a locally built ONNX Runtime can load and run the AutoSync
// models. Build it against the same reduced runtime that is packaged into the
// app:
//
//   g++ -std=c++17 verify-runtime.cpp \
//       -I<onnxruntime>/include/onnxruntime/core/session \
//       -I<onnxruntime>/include \
//       -L<build-dir>/Release -lonnxruntime -o verify-runtime
//
//   LD_LIBRARY_PATH=<build-dir>/Release ./verify-runtime model.onnx [model.onnx ...]
//
// The program creates a session with the same options the app uses
// (ORT_ENABLE_ALL graph optimizations) and runs one inference pass over a one
// second window of silence, which exercises op kernels, shape inference and
// the fused kernels the graph optimizers introduce.

#include <cstdint>
#include <iostream>
#include <string>
#include <vector>

#include "onnxruntime_cxx_api.h"

namespace {

// The published models take the raw audio samples as a float tensor with a
// dynamic length. A one second window (16 kHz) is enough for the convolutional
// front end to produce a valid feature map.
constexpr int64_t kSampleCount = 16'000;

Ort::Value MakeInput(Ort::Session& session, size_t index) {
  Ort::AllocatorWithDefaultOptions allocator;
  auto type_info = session.GetInputTypeInfo(index);
  auto tensor_info = type_info.GetTensorTypeAndShapeInfo();
  const ONNXTensorElementDataType element_type = tensor_info.GetElementType();

  std::vector<int64_t> shape = tensor_info.GetShape();
  for (auto& dim : shape) {
    if (dim <= 0) dim = kSampleCount;
  }
  size_t element_count = 1;
  for (auto dim : shape) element_count *= static_cast<size_t>(dim);

  auto memory = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
  if (element_type == ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64) {
    // Integer inputs are counters (for example the number of tokens); zero
    // would be rejected by the graph, so use one.
    std::vector<int64_t> values(element_count, 1);
    return Ort::Value::CreateTensor<int64_t>(memory, values.data(), values.size(),
                                              shape.data(), shape.size());
  }
  std::vector<float> zeros(element_count, 0.0f);
  return Ort::Value::CreateTensor<float>(memory, zeros.data(), zeros.size(),
                                         shape.data(), shape.size());
}

}  // namespace

int main(int argc, char** argv) {
  if (argc < 2) {
    std::cerr << "usage: verify-runtime <model.onnx> [...]" << std::endl;
    return 2;
  }

  int failures = 0;
  try {
    Ort::Env env(ORT_LOGGING_LEVEL_WARNING, "verify-runtime");
    Ort::SessionOptions options;
    options.SetIntraOpNumThreads(2);
    options.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);

    for (int i = 1; i < argc; ++i) {
      const std::string path = argv[i];
      try {
        Ort::Session session(env, path.c_str(), options);
        const size_t input_count = session.GetInputCount();

        std::vector<Ort::AllocatedStringPtr> names;
        std::vector<Ort::Value> inputs;
        inputs.reserve(input_count);
        for (size_t n = 0; n < input_count; ++n) {
          auto name = session.GetInputNameAllocated(n, Ort::AllocatorWithDefaultOptions{});
          names.push_back(std::move(name));
          inputs.push_back(MakeInput(session, n));
        }

        std::vector<const char*> input_name_ptrs;
        input_name_ptrs.reserve(names.size());
        for (auto& name : names) input_name_ptrs.push_back(name.get());

        // The output names are requested explicitly: the runtime returns one
        // tensor per requested output.
        std::vector<Ort::AllocatedStringPtr> output_names;
        std::vector<const char*> output_name_ptrs;
        const size_t output_count = session.GetOutputCount();
        for (size_t n = 0; n < output_count; ++n) {
            output_names.push_back(
                session.GetOutputNameAllocated(n, Ort::AllocatorWithDefaultOptions{})
            );
        }
        output_name_ptrs.reserve(output_names.size());
        for (auto& name : output_names) output_name_ptrs.push_back(name.get());

        auto outputs = session.Run(Ort::RunOptions{nullptr}, input_name_ptrs.data(), inputs.data(),
                                   inputs.size(), output_name_ptrs.data(), output_name_ptrs.size());
        std::cout << "OK   " << path << " inputs=" << input_count << " outputs=" << outputs.size()
                  << std::endl;
        for (size_t n = 0; n < outputs.size(); ++n) {
          auto info = outputs[n].GetTensorTypeAndShapeInfo();
          std::cout << "       output[" << n << "] shape=";
          for (auto dim : info.GetShape()) std::cout << dim << " ";
          std::cout << std::endl;
        }
      } catch (const std::exception& error) {
        std::cout << "FAIL " << path << ": " << error.what() << std::endl;
        ++failures;
      }
    }
  } catch (const std::exception& error) {
    std::cerr << "fatal: " << error.what() << std::endl;
    return 2;
  }

  return failures == 0 ? 0 : 1;
}
