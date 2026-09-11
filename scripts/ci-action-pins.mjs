export const ACTION_PINS = new Map([
  ['actions/checkout', { version: 'v7', sha: '3d3c42e5aac5ba805825da76410c181273ba90b1' }],
  ['actions/setup-node', { version: 'v7', sha: '820762786026740c76f36085b0efc47a31fe5020' }],
  ['pnpm/action-setup', { version: 'v6', sha: '0977fd99725f1db4007ccb2928dbb4e90d06cc86' }],
  ['actions/cache', { version: 'v6', sha: '55cc8345863c7cc4c66a329aec7e433d2d1c52a9' }],
  ['actions/upload-artifact', { version: 'v7', sha: '043fb46d1a93c77aae656e7c1c64a875d1fc6a0a' }],
  ['actions/setup-python', { version: 'v5', sha: 'a26af69be951a213d495a4c3e4e4022e16d87065' }],
  ['actions/configure-pages', { version: 'v5', sha: '983d7736d9b0ae728b81ab479565c72886d7745b' }],
  ['actions/upload-pages-artifact', { version: 'v4', sha: '7b1f4a764d45c48632c6b24a0339c27f5614fb0b' }],
  ['actions/deploy-pages', { version: 'v4', sha: 'd6db90164ac5ed86f2b6aed7e0febac5b3c0c03e' }],
]);

export function actionPin(action) {
  return ACTION_PINS.get(action) ?? null;
}
