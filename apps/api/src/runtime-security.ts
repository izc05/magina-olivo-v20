export function assertSafeRuntimeEnvironment(env: NodeJS.ProcessEnv = process.env) {
  if (env.NODE_ENV !== 'production') return;

  const violations: string[] = [];
  if (env.ALLOW_DEV_AUTH_HEADERS === 'true') {
    violations.push('ALLOW_DEV_AUTH_HEADERS must not be true in production');
  }

  if (violations.length) {
    throw new Error(`unsafe_production_configuration: ${violations.join('; ')}`);
  }
}
