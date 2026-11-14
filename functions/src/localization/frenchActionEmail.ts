import { escapeEmailHtml } from './language';

/** Pure French counterpart for action emails. Links and names are always escaped. */
export function frenchActionEmail(params: {
  name?: string; title: string; body: string; cta: string; url: string; footer: string;
}): { html: string; text: string } {
  const name = params.name?.trim();
  const greeting = name ? `Bonjour ${name},` : 'Bonjour,';
  const e = escapeEmailHtml;
  return {
    html: `<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8"></head>
<body style="margin:0;padding:24px;background:#FAF6EC;color:#252320;font-family:Arial,sans-serif">
<table role="presentation" style="max-width:480px;margin:auto;background:#fff;border:1px solid #E5E3DF;padding:32px"><tr><td>
<p style="color:#2C3E7C;font-weight:bold">StitchPad</p><h1 style="font-family:Georgia,serif;color:#2C3E7C">${e(params.title)}</h1>
<p>${e(greeting)}</p><p style="line-height:1.6">${e(params.body)}</p>
<p><a href="${e(params.url)}" style="display:inline-block;padding:14px 24px;background:#2C3E7C;color:white;text-decoration:none">${e(params.cta)}</a></p>
<p style="font-size:12px">Si le bouton ne fonctionne pas, copiez ce lien dans votre navigateur :<br><a href="${e(params.url)}">${e(params.url)}</a></p>
<p style="font-size:12px;color:#57534C">${e(params.footer)}</p><p style="font-size:12px">Besoin d’aide ? support@getstitchpad.com</p>
</td></tr></table></body></html>`,
    text: `${params.title}\n\n${greeting}\n${params.body}\n\n${params.cta} : ${params.url}\n\n${params.footer}\nBesoin d’aide ? support@getstitchpad.com`,
  };
}
