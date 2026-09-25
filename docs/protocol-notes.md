# Protocol Notes

The client follows the Verzeta remote-access protocol:

- Single endpoint: `/ws`.
- Client ops include `op`, `request_id`, and `params`.
- Server responses carry `type=response`, the original `request_id`, `ok`, and
  either `data` or `error`.
- Server events carry `type=event`, `event`, and `data`.
- The only initial unauthenticated ops are `auth.pair` and `auth.token`.
- A paired device has desktop-equivalent access; v1 has no roles or scopes.
- Tokens are stored on-device and sent only inside the WebSocket auth op.

All future chat and streaming features should build on one `RemoteSession`.
