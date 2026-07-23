import { rewriteActionLinkHost, ACTION_LINK_HOST } from '../../auth/actionLinkHost';

describe('rewriteActionLinkHost', () => {
  it('rewrites the default firebaseapp.com host to our own domain', () => {
    const generated =
      'https://stitchpad-30607.firebaseapp.com/__/auth/action' +
      '?mode=verifyEmail&oobCode=ABC123&apiKey=AIzaSyKEY&lang=en';

    expect(rewriteActionLinkHost(generated)).toBe(
      `https://${ACTION_LINK_HOST}/__/auth/action` +
        '?mode=verifyEmail&oobCode=ABC123&apiKey=AIzaSyKEY&lang=en',
    );
  });

  it('preserves the oobCode exactly, including characters that survive encoding', () => {
    const oob = 'lyfVzfi7kz_QsZSgPIaybsOJqyFk9-M2eNgWUPUDtFcAAAGerdUunw';
    const rewritten = rewriteActionLinkHost(
      `https://stitchpad-30607.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=${oob}`,
    );

    expect(rewritten).toContain(`oobCode=${oob}`);
    expect(rewritten.startsWith(`https://${ACTION_LINK_HOST}/`)).toBe(true);
  });

  it('rewrites the web.app host too', () => {
    expect(
      rewriteActionLinkHost('https://stitchpad-30607.web.app/__/auth/action?mode=verifyEmail'),
    ).toBe(`https://${ACTION_LINK_HOST}/__/auth/action?mode=verifyEmail`);
  });

  it('leaves a link already on our domain untouched', () => {
    const already = `https://${ACTION_LINK_HOST}/__/auth/action?mode=verifyEmail&oobCode=X`;

    expect(rewriteActionLinkHost(already)).toBe(already);
  });

  it('leaves an unrecognised host untouched rather than guessing', () => {
    // If Firebase ever changes the default host, we must not silently point
    // users at a domain that was never verified — better to keep the working
    // link and let the smoke test catch the drift.
    const foreign = 'https://something-else.example.com/__/auth/action?mode=verifyEmail';

    expect(rewriteActionLinkHost(foreign)).toBe(foreign);
  });

  it('returns the input unchanged when it is not a parseable URL', () => {
    expect(rewriteActionLinkHost('not a url')).toBe('not a url');
  });
});
