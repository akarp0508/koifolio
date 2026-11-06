import { t } from '../i18n'
export function TagFilter({ label, tags, selected, onChange }: {
  label: string
  tags: string[]
  selected: string[]
  onChange: (selected: string[]) => void
}) {
  const available = tags.filter((tag) => !selected.includes(tag))

  return (
    <div className="tag-filter">
      <span>{label}</span>
      {selected.map((tag) => (
        <button key={tag} className="tag" aria-label={t('Remove {name}', { name: tag })}
                onClick={() => onChange(selected.filter((other) => other !== tag))}>{tag} ×</button>
      ))}
      {available.length > 0 && (
        <select aria-label={t('Add {name}', { name: label })} value="" onChange={(e) => onChange([...selected, e.target.value])}>
          <option value="">{t('+ tag')}</option>
          {available.map((tag) => <option key={tag} value={tag}>{tag}</option>)}
        </select>
      )}
      {tags.length === 0 && <small className="muted">none yet</small>}
    </div>
  )
}
