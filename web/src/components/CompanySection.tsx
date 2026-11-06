import { RISK_LABELS } from '../plans'
import { Markdown } from './Markdown'
import { Tags } from './Tags'
import { t } from '../i18n'

export function CompanySection({ title, sector, risk, thesis, analysis, tags, onEdit }: {
  title: string
  sector?: string
  risk?: number
  thesis?: string
  analysis?: string
  tags?: string[]
  onEdit: () => void
}) {
  const empty = !sector && !risk && !thesis && !analysis && !tags?.length
  return (
    <section className="panel">
      <header>
        <h2>{title}</h2>
        <button onClick={onEdit}>{t('Edit')}</button>
      </header>
      {empty && <p className="muted">{t('Nothing here yet. Add a sector, risk score and your analysis.')}</p>}
      {sector && <p className="muted">{sector}</p>}
      <Tags tags={tags} />
      {risk !== undefined && <p>Risk: {RISK_LABELS[risk]}</p>}
      {thesis && <p><strong>{t('Thesis:')}</strong> {thesis}</p>}
      {analysis && <Markdown text={analysis} />}
    </section>
  )
}
