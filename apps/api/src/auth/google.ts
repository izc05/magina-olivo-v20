import { OAuth2Client } from 'google-auth-library';

export type GoogleIdentityClaims = {
  subject: string;
  email: string | null;
  emailVerified: boolean;
  displayName: string;
  pictureUrl: string | null;
  givenName: string | null;
  familyName: string | null;
  hostedDomain: string | null;
};

export interface GoogleIdentityVerifier {
  verify(credential: string): Promise<GoogleIdentityClaims>;
}

export class GoogleIdentityNotConfiguredError extends Error {
  constructor() {
    super('Google Sign-In is not configured.');
    this.name = 'GoogleIdentityNotConfiguredError';
  }
}

export class UnavailableGoogleIdentityVerifier implements GoogleIdentityVerifier {
  async verify(): Promise<GoogleIdentityClaims> {
    throw new GoogleIdentityNotConfiguredError();
  }
}

export function createGoogleIdentityVerifier(clientId: string): GoogleIdentityVerifier {
  const client = new OAuth2Client(clientId);

  return {
    async verify(credential: string): Promise<GoogleIdentityClaims> {
      const ticket = await client.verifyIdToken({ idToken: credential, audience: clientId });
      const payload = ticket.getPayload();
      if (!payload?.sub) throw new Error('Google ID token has no subject.');

      const fallbackName = payload.email?.split('@')[0] || 'Usuario Mágina';
      return {
        subject: payload.sub,
        email: payload.email ?? null,
        emailVerified: payload.email_verified === true,
        displayName: payload.name?.trim() || fallbackName,
        pictureUrl: payload.picture ?? null,
        givenName: payload.given_name ?? null,
        familyName: payload.family_name ?? null,
        hostedDomain: payload.hd ?? null,
      };
    },
  };
}

export function createGoogleIdentityVerifierFromEnv(): GoogleIdentityVerifier | null {
  const clientId = process.env.GOOGLE_CLIENT_ID?.trim();
  return clientId ? createGoogleIdentityVerifier(clientId) : null;
}
