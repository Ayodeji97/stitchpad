/**
 * Firebase Auth generates email action links on the project's default host,
 * `stitchpad-30607.firebaseapp.com`. That domain is heavily abused for phishing,
 * so carrier DNS and safe-browsing filters — common on Nigerian mobile networks —
 * blocklist it wholesale, and the user gets "This site can't be reached" instead
 * of a verification page. Verification is a hard signup gate, so an affected user
 * is locked out entirely.
 *
 * The fix is to serve the same link from a domain we own. Firebase Hosting serves
 * the identical `/__/auth/action` handler on every domain attached to the project
 * (byte-identical response), and the link carries its own apiKey and oobCode, so
 * only the host differs.
 *
 * This rewrite exists because Firebase's own "Customize action URL" setting is
 * rejected server-side with EMAIL_TEMPLATE_UPDATE_NOT_ALLOWED (both via console
 * and the Identity Toolkit admin API, as of 2026-07-22). If that restriction is
 * ever lifted, set the action URL project-wide and delete this module.
 *
 * See docs/auth/verification-action-url-custom-domain.md
 */

/** Our own Firebase Hosting domain, attached to the same site. */
export const ACTION_LINK_HOST = 'auth.getstitchpad.com';

/** Default hosts Firebase generates links on, both pointing at the same site. */
const DEFAULT_HOSTS = ['stitchpad-30607.firebaseapp.com', 'stitchpad-30607.web.app'];

/**
 * Swaps a generated action link onto [ACTION_LINK_HOST], leaving path and query
 * untouched. Any host we don't recognise is left alone: if Firebase changes its
 * default, keeping the working link beats pointing users at an unverified domain.
 */
export function rewriteActionLinkHost(link: string): string {
  let url: URL;
  try {
    url = new URL(link);
  } catch {
    return link;
  }

  if (!DEFAULT_HOSTS.includes(url.hostname)) {
    return link;
  }

  url.hostname = ACTION_LINK_HOST;
  return url.toString();
}
