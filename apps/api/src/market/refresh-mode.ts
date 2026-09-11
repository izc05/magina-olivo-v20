export type MarketRefreshMode = 'source-check' | 'dry-run' | 'apply';

const modeByFlag = new Map<string, MarketRefreshMode>([
  ['--source-check', 'source-check'],
  ['--dry-run', 'dry-run'],
  ['--apply', 'apply'],
]);

export function parseMarketRefreshMode(args: string[]): MarketRefreshMode {
  const flags = args.filter((arg) => arg.startsWith('--'));
  const unknown = flags.find((flag) => !modeByFlag.has(flag));
  if (unknown) throw new Error(`market_refresh_mode_unknown:${unknown}`);

  const selected = flags.map((flag) => modeByFlag.get(flag)).filter((mode): mode is MarketRefreshMode => Boolean(mode));
  if (selected.length > 1) throw new Error('market_refresh_mode_conflict');

  return selected[0] ?? 'source-check';
}

export function marketRefreshModeNeedsDatabase(mode: MarketRefreshMode): boolean {
  return mode !== 'source-check';
}
