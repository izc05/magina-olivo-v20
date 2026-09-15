export type MarketRefreshMode = 'source-check' | 'dry-run' | 'apply';

export type MarketRefreshOperation = {
  mode: MarketRefreshMode;
  allowCorrections: boolean;
};

const modeByFlag = new Map<string, MarketRefreshMode>([
  ['--source-check', 'source-check'],
  ['--dry-run', 'dry-run'],
  ['--apply', 'apply'],
]);

const modifierFlags = new Set(['--allow-corrections']);

export function parseMarketRefreshOperation(args: string[]): MarketRefreshOperation {
  const flags = args.filter((arg) => arg.startsWith('--'));
  const unknown = flags.find((flag) => !modeByFlag.has(flag) && !modifierFlags.has(flag));
  if (unknown) throw new Error(`market_refresh_mode_unknown:${unknown}`);

  const selected = flags.map((flag) => modeByFlag.get(flag)).filter((mode): mode is MarketRefreshMode => Boolean(mode));
  if (selected.length > 1) throw new Error('market_refresh_mode_conflict');

  const mode = selected[0] ?? 'source-check';
  const allowCorrections = flags.includes('--allow-corrections');
  if (allowCorrections && mode !== 'apply') {
    throw new Error('market_refresh_correction_approval_requires_apply');
  }

  return { mode, allowCorrections };
}

export function parseMarketRefreshMode(args: string[]): MarketRefreshMode {
  return parseMarketRefreshOperation(args).mode;
}

export function marketRefreshModeNeedsDatabase(mode: MarketRefreshMode): boolean {
  return mode !== 'source-check';
}
