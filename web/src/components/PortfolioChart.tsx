import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { PortfolioPoint } from '../api/client'
import { formatAmount, formatGain } from '../format'
import { t } from '../i18n'

type ChartPoint = PortfolioPoint & { valueNumber: number; costNumber: number }

function ChartTooltip({ active, payload, currency }: {
  active?: boolean
  payload?: { payload: ChartPoint }[]
  currency: string
}) {
  const point = payload?.[0]?.payload
  if (!active || !point) {
    return null
  }
  return (
    <div className="chart-tooltip">
      <strong>{point.date}</strong>
      <div><span>{t('Value')}</span>{formatAmount(point.value ?? '')} {currency}</div>
      <div><span>{t('Cost')}</span>{formatAmount(point.cost ?? '')} {currency}</div>
      <div><span>{t('Total gain')}</span>{formatGain(point.gain ?? '')} {currency}</div>
    </div>
  )
}

/** Strings become numbers here, for drawing only. */
export function PortfolioChart({ points, currency }: { points: PortfolioPoint[]; currency: string }) {
  const data: ChartPoint[] = points.map((point) => ({
    ...point,
    valueNumber: Number(point.value),
    costNumber: Number(point.cost),
  }))
  const growing = data.length > 1 && data[data.length - 1].valueNumber >= data[0].valueNumber

  return (
    <>
      <div className="chart">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
            <CartesianGrid stroke="var(--border)" vertical={false} />
            <XAxis dataKey="date" minTickGap={60} tick={{ fill: 'var(--muted)', fontSize: 12 }} stroke="var(--border)" />
            <YAxis width={72} domain={['auto', 'auto']} tick={{ fill: 'var(--muted)', fontSize: 12 }} stroke="var(--border)" />
            <Tooltip content={<ChartTooltip currency={currency} />} />
            <Line dataKey="costNumber" stroke="var(--muted)" strokeDasharray="4 4" dot={false} type="stepAfter"
                  isAnimationActive={false} />
            <Line dataKey="valueNumber" stroke={growing ? 'var(--gain)' : 'var(--loss)'} strokeWidth={2} dot={false}
                  isAnimationActive={false} />
          </LineChart>
        </ResponsiveContainer>
      </div>
      <div className="chart-legend">
        <span>━ value</span>
        <span>╌ what you paid</span>
      </div>
    </>
  )
}
