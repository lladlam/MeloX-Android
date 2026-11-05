import { definePlugin, PluginError } from "../../src/index.js";
import tone from "../test-tone/index.js";

// Public functions stay available signed out. Use only a disposable test token here.
export default definePlugin({
  ...tone,
  async authenticationStatus(host) {
    if (!host.account) return { type: "signedOut" };
    const response = await host.http.request({ url: "https://httpbingo.org/bearer", credential: "active" });
    if (response.status === 401) return { type: "expired", accountId: host.account.id };
    if (response.status !== 200) throw new PluginError("NETWORK_REQUEST_FAILED");
    // Do not return/log the bearer endpoint's token echo.
    return { type: "signedIn", account: host.account };
  },
});
