import DOMPurify from 'dompurify';

export function safeHtml(node: HTMLElement, html: string) {
    const update = (value: string) => {
        node.innerHTML = DOMPurify.sanitize(value);
    };

    update(html);

    return {
        update,
        destroy() {
            node.innerHTML = '';
        }
    };
}