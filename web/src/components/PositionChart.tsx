import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { HistoryPoint } from '../api/client'
import { formatAmount } from '../format'

type ChartPoint = HistoryPoint & { valueNumber?: number; costNumber?: number; closeNumber: number }

function ChartTooltip({ active, payload, currency, priceOnly }: {
  active?: boolean
  payload?: { payload: ChartPoint }[]
  currency: string
  priceOnly: boolean
}) {
  const point = payload?.[0]?.payload
  if (!active || !point) {
    return null
  }
  return (
    <div className="chart-tooltip">
      <strong>{point.date}</strong>
      {!priceOnly && <div><span>Value</span>{formatAmount(point.value ?? '')} {currency}</div>}
      {!priceOnly && <div><span>Cost</span>{formatAmount(point.cost ?? '')} {currency}</div>}
      <div><span>Quantity</span>{point.quantity}</div>
      <div><span>Close</span>{formatAmount(point.close ?? '')}</div>
      {point.traded && <div><span>Bought or sold</span></div>}
    </div>
  )
}

function TradeDot({ cx, cy, payload }: { cx?: number; cy?: number; payload?: ChartPoint }) {
  if (!payload?.traded || cx === undefined || cy === undefined) {
    return null
  }
  return <circle cx={cx} cy={cy} r={3.5} fill="var(--accent)" stroke="var(--bg)" />
}

/** Plots value and cost, or only the price when there's no value yet. Strings become numbers here, for drawing only. */
export function PositionChart({ points, currency, priceOnly }: {
  points: HistoryPoint[]
  currency: string
  priceOnly: boolean
}) {
  const data: ChartPoint[] = points.map((point) => ({
    ...point,
    valueNumber: point.value === undefined ? undefined : Number(point.value),
    costNumber: point.cost === undefined ? undefined : Number(point.cost),
    closeNumber: Number(point.close),
  }))

  return (
    <>
      <div className="chart">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
            <CartesianGrid stroke="var(--border)" vertical={false} />
            <XAxis dataKey="date" minTickGap={60} tick={{ fill: 'var(--muted)', fontSize: 12 }} stroke="var(--border)" />
            <YAxis width={64} domain={['auto', 'auto']} tick={{ fill: 'var(--muted)', fontSize: 12 }} stroke="var(--border)" />
            <Tooltip content={<ChartTooltip currency={currency} priceOnly={priceOnly} />} />
            {priceOnly ? (
              <Line dataKey="closeNumber" stroke="var(--accent)" strokeWidth={2} dot={false} isAnimationActive={false} />
            ) : (
              <>
                <Line dataKey="costNumber" stroke="var(--muted)" strokeDasharray="4 4" dot={false} type="stepAfter"
                      isAnimationActive={false} />
                <Line dataKey="valueNumber" stroke="var(--accent)" strokeWidth={2} dot={<TradeDot />} activeDot={false}
                      isAnimationActive={false} />
              </>
            )}
          </LineChart>
        </ResponsiveContainer>
      </div>
      {!priceOnly && (
        <div className="chart-legend">
          <span>━ value</span>
          <span>╌ what you paid</span>
          <span>● bought or sold</span>
        </div>
      )}
    </>
  )
}
