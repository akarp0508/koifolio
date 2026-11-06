import ReactMarkdown from 'react-markdown'

/** Raw HTML in the text is not rendered, so pasted or agent-written notes can't run anything. */
export function Markdown({ text }: { text: string }) {
  return <div className="markdown"><ReactMarkdown>{text}</ReactMarkdown></div>
}
