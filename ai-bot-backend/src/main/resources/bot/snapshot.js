// Builds a compact HTML view of what is visible on the page, for the LLM and the content extractor.
// Every interactive element gets a data-bot-id, so the agent can later click or type into it by that number.
const maxLength = arguments[0];

const SKIPPED_TAGS = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEMPLATE', 'SVG', 'CANVAS', 'IFRAME', 'LINK', 'META', 'OBJECT', 'EMBED']);
const KEPT_TAGS = new Set([
    'A', 'BUTTON', 'INPUT', 'TEXTAREA', 'SELECT', 'OPTION', 'LABEL', 'FORM', 'IMG', 'VIDEO',
    'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'P', 'UL', 'OL', 'LI', 'ARTICLE', 'SECTION', 'HEADER',
    'FOOTER', 'NAV', 'MAIN', 'ASIDE', 'TIME', 'BLOCKQUOTE', 'TABLE', 'TR', 'TD', 'TH', 'SUMMARY', 'DIALOG'
]);
const VOID_TAGS = new Set(['INPUT', 'IMG']);
const KEPT_ATTRIBUTES = ['id', 'href', 'src', 'alt', 'title', 'aria-label', 'placeholder', 'type', 'name', 'role', 'datetime'];
const INTERACTIVE = 'a[href], button, input:not([type=hidden]), textarea, select, summary, [role=button], [role=link], '
    + '[role=tab], [role=menuitem], [role=checkbox], [role=textbox], [role=searchbox], [contenteditable=""], [contenteditable=true]';

let nextId = 1;
document.querySelectorAll('[data-bot-id]').forEach(element => element.removeAttribute('data-bot-id'));

function escapeText(text) {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function escapeAttribute(text) {
    return escapeText(text).replace(/"/g, '&quot;');
}

function attributesOf(element, botId) {
    let result = botId ? ` data-bot-id="${botId}"` : '';
    for (const name of KEPT_ATTRIBUTES) {
        let value = element.getAttribute(name);
        if (!value) {
            continue;
        }
        if (name === 'href' || name === 'src') {
            value = element[name] || value;
            if (value.startsWith('data:') || value.startsWith('javascript:')) {
                continue;
            }
        }
        if (name === 'id' && value.length > 40) {
            continue;
        }
        result += ` ${name}="${escapeAttribute(value.slice(0, 300))}"`;
    }
    if (element.tagName === 'INPUT' && element.type !== 'password' && element.value) {
        result += ` value="${escapeAttribute(element.value.slice(0, 100))}"`;
    }
    return result;
}

function render(node) {
    if (node.nodeType === Node.TEXT_NODE) {
        return escapeText(node.textContent.replace(/\s+/g, ' '));
    }
    if (node.nodeType !== Node.ELEMENT_NODE || SKIPPED_TAGS.has(node.tagName)) {
        return '';
    }

    const style = window.getComputedStyle(node);
    if (style.display === 'none' || style.visibility === 'hidden') {
        return '';
    }
    if (style.display !== 'contents') {
        const rect = node.getBoundingClientRect();
        if (rect.width === 0 && rect.height === 0) {
            return '';
        }
    }

    const interactive = node.matches(INTERACTIVE);
    const botId = interactive ? nextId++ : null;
    if (botId) {
        node.setAttribute('data-bot-id', botId);
    }

    let children = '';
    for (const child of node.childNodes) {
        children += render(child);
    }
    if (node.shadowRoot) {
        for (const child of node.shadowRoot.childNodes) {
            children += render(child);
        }
    }

    // plain wrappers (div, span, ...) only add noise, so we keep just their content
    const hasShortId = node.id && node.id.length <= 40;
    if (!interactive && !KEPT_TAGS.has(node.tagName) && !hasShortId && !node.getAttribute('role')) {
        const isBlock = style.display !== 'inline' && style.display !== 'contents';
        return isBlock ? `\n${children}\n` : children;
    }

    const tag = node.tagName.toLowerCase();
    if (VOID_TAGS.has(node.tagName)) {
        return `<${tag}${attributesOf(node, botId)}>`;
    }
    return `<${tag}${attributesOf(node, botId)}>${children.trim()}</${tag}>`;
}

const header = `<!-- scroll ${Math.round(window.scrollY)} of ${document.documentElement.scrollHeight}px -->\n`;
let content = render(document.body).replace(/\n\s*\n+/g, '\n').trim();
if (content.length > maxLength) {
    content = content.slice(0, maxLength) + '\n<!-- truncated -->';
}
return header + content;
