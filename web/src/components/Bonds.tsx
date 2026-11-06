import { useCallback, useEffect, useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Account, Bond } from '../api/client'
import { decimal, formatAmount, formatGain, gainClass } from '../format'
import { BondDialog } from './BondDialog'
import { ConfirmDialog } from './ConfirmDialog'
import { t } from '../i18n'

export function Bonds() {
  const [bonds, setBonds] = useState<Bond[] | null>(null)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [adding, setAdding] = useState(false)
  const [removing, setRemoving] = useState<Bond | null>(null)
  const [error, setError] = useState<ApiError | null>(null)

  const load = useCallback(() =>
    Promise.all([api.bonds(), api.listAccounts()])
      .then(([bondList, accountList]) => {
        setBonds(bondList)
        setAccounts(accountList)
        setError(null)
      })
      .catch((e: ApiError) => setError(e)), [])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <>
      <header className="page-header">
        <h1>{t('Bonds')}</h1>
        <button className="primary" onClick={() => setAdding(true)}>{t('+ Bonds')}</button>
      </header>

      {error && <p className="error" role="alert">{error.message} <button className="link" onClick={load}>{t('Try again')}</button></p>}
      {!bonds && !error && <p className="muted">{t('Loading…')}</p>}

      {bonds && (
        <>
          <p className="muted">{t("Polish TOS, ROS, EDO and ROD bonds in PLN. Value grows daily from each bond's rate, as in the official interest tables, and counts in your totals. Redeeming shows what you'd get after the early redemption fee, before tax.")}</p>
          {bonds.length === 0 && <p className="muted">{t('No bonds yet.')}</p>}
          {bonds.length > 0 && (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{t('Series')}</th>
                    <th>{t('Account')}</th>
                    <th className="number">{t('Bonds')}</th>
                    <th>{t('Bought')}</th>
                    <th>{t('Matures')}</th>
                    <th className="number">{t('Rates')}</th>
                    <th className="number">{t('Value')}</th>
                    <th className="number">{t('Interest')}</th>
                    <th className="number">{t('If redeemed')}</th>
                    <th><span className="visually-hidden">{t('Actions')}</span></th>
                  </tr>
                </thead>
                <tbody>
                  {bonds.map((bond) => (
                    <tr key={bond.id}>
                      <td>
                        {bond.series}
                        {bond.rateAssumed && <span className="not-in-balance">this year's rate missing</span>}
                        {bond.matured && <span className="not-in-balance">matured</span>}
                      </td>
                      <td>{bond.accountName}</td>
                      <td className="number">{bond.quantity}</td>
                      <td className="nowrap">{bond.purchaseDate}</td>
                      <td className="nowrap">{bond.maturityDate}</td>
                      <td className="number">{bond.rates?.map((rate) => `${decimal(rate ?? '')}%`).join(', ')}</td>
                      <td className="number">{formatAmount(bond.value ?? '')}</td>
                      <td className={`number ${gainClass(bond.interest)}`}>{formatGain(bond.interest ?? '')}</td>
                      <td className="number">{formatAmount(bond.ifRedeemedToday ?? '')}</td>
                      <td className="actions">
                        <button className="icon" aria-label={t('Remove {name}', { name: bond.series ?? '' })} title={t('Remove')} onClick={() => setRemoving(bond)}>✕</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {adding && (
        <BondDialog
          accounts={accounts}
          onSaved={() => {
            setAdding(false)
            void load()
          }}
          onClose={() => setAdding(false)}
        />
      )}
      {removing && (
        <ConfirmDialog
          title={t('Remove {name}?', { name: removing.series ?? '' })}
          message={t("Use this for a mistake or once it's redeemed. If it was paid from the account's cash, the cost goes back there.")}
          action={t('Remove')}
          onConfirm={() => api.deleteBond(removing.id ?? '')}
          onDone={() => {
            setRemoving(null)
            void load()
          }}
          onClose={() => setRemoving(null)}
        />
      )}
    </>
  )
}
