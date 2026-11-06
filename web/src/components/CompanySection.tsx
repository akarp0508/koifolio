import { RISK_LABELS } from '../plans'
import { Markdown } from './Markdown'

export function CompanySection({ title, sector, risk, thesis, analysis, onEdit }: {
  title: string
  sector?: string
  risk?: number
  thesis?: string
  analysis?: string
  onEdit: () => void
}) {
  const empty = !sector && !risk && !thesis && !analysis
  return (
    <section className="panel">
      <header>
        <h2>{title}</h2>
        <button onClick={onEdit}>Edit</button>
      </header>
      {empty && <p className="muted">Nothing here yet. Add a sector, risk score and your analysis.</p>}
      {sector && <p className="muted">{sector}</p>}
      {risk !== undefined && <p>Risk: {RISK_LABELS[risk]}</p>}
      {thesis && <p><strong>Thesis:</strong> {thesis}</p>}
      {analysis && <Markdown text={analysis} />}
    </section>
  )
}
