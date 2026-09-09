import { z } from 'zod';

export const googleSignInSchema = z.object({
  credential: z.string().trim().min(100).max(20_000),
});

export const selectWorkspaceSchema = z.object({
  workspace_id: z.string().uuid(),
});

export type GoogleSignInInput = z.infer<typeof googleSignInSchema>;
export type SelectWorkspaceInput = z.infer<typeof selectWorkspaceSchema>;
