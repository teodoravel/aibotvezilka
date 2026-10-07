// Finds the visible element whose text or label best matches a free-form description,
// e.g. "Прикажи повеќе" or "the 'Пребарај' input". Returns null when nothing matches.
const description = arguments[0];
const wantsInput = arguments[1];

const TEXT_INPUTS = 'input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=checkbox]):not([type=radio]), '
    + 'textarea, [contenteditable=""], [contenteditable=true], [role=textbox], [role=searchbox]';
const CLICKABLE = 'a[href], button, input[type=submit], input[type=button], summary, label, [role=button], [role=link], '
    + '[role=tab], [role=menuitem], [role=option], [onclick], [data-bot-id]';

function normalize(text) {
    return (text || '').toLowerCase().replace(/\s+/g, ' ').trim();
}

function labelOf(element) {
    return normalize([
        element.getAttribute('aria-label'),
        element.getAttribute('placeholder'),
        element.getAttribute('title'),
        element.innerText,
        element.value,
        element.querySelector('img[alt]')?.alt
    ].filter(Boolean).join(' '));
}

function isVisible(element) {
    const rect = element.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0 && window.getComputedStyle(element).visibility !== 'hidden';
}

// when the description quotes the text ("the 'Барај' button"), only the quoted part is searched for
const quoted = description.match(/["'„“”‘’«](.+?)["'“”‘’»]/);
const needle = normalize(quoted ? quoted[1] : description);

let best = null;
let bestScore = 0;
let bestLength = Infinity;
for (const element of document.querySelectorAll(wantsInput ? TEXT_INPUTS : CLICKABLE)) {
    if (!isVisible(element)) {
        continue;
    }
    const label = labelOf(element);
    if (!label) {
        continue;
    }

    let score = 0;
    if (label === needle) {
        score = 4;
    } else if (label.startsWith(needle)) {
        score = 3;
    } else if (label.includes(needle)) {
        score = 2;
    } else if (label.length > 2 && needle.includes(label)) {
        score = 1;
    }

    // on a tie the shorter label wins, it is usually the more specific element
    if (score > bestScore || (score > 0 && score === bestScore && label.length < bestLength)) {
        best = element;
        bestScore = score;
        bestLength = label.length;
    }
}

if (!best && wantsInput) {
    best = [...document.querySelectorAll(TEXT_INPUTS)].find(isVisible) || null;
}
return best;
