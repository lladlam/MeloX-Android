import {definePlugin, PluginError} from '../../src/index.js';

// Replace this origin AND the manifest domains/endpoints with your controlled fixture origin.
// Never add a client secret. The host owns PKCE, callback validation and encrypted token storage.
const origin = 'https://oauth.example.com';

export default definePlugin({
  async authenticationStatus(host) {
    if (!host.account) return {type: 'signedOut'};
    const result = await host.http.request({url: origin + '/api/me', credential: 'active'});
    if (result.status === 401) return {type: 'expired', accountId: host.account.id};
    if (result.status !== 200) throw new PluginError('NETWORK_REQUEST_FAILED');
    // Only parse the standardized synthetic profile; never log bodies or headers.
    const profile: unknown = JSON.parse(result.body);
    if (!profile || typeof profile !== 'object' || !('displayName' in profile) || typeof profile.displayName !== 'string')
      throw new PluginError('PLUGIN_ERROR');
    return {type: 'signedIn', account: {id: host.account.id, displayName: profile.displayName}};
  },
  async search(_query, page, pageSize, host) {
    if (!host.account) throw new PluginError('LOGIN_REQUIRED');
    const result = await host.http.request({url: origin + '/api/me', credential: 'active'});
    if (result.status === 401) throw new PluginError('AUTH_EXPIRED');
    if (result.status !== 200) throw new PluginError('NETWORK_REQUEST_FAILED');
    return {items: [], page, pageSize, total: 0, hasMore: false};
  },
});
