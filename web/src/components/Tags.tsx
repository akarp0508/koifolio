export function Tags({ tags }: { tags?: string[] }) {
  if (!tags || tags.length === 0) {
    return null
  }
  return <p className="tags">{tags.map((tag) => <span key={tag} className="tag">{tag}</span>)}</p>
}
