import { useState } from 'react'
import { ApiError } from '../api/client'
import { Modal } from './Modal'
import { t } from '../i18n'

export function ConfirmDialog({ title, message, action = t('Delete'), onConfirm, onDone, onClose }: {
  title: string
  message: string
  action?: string
  onConfirm: () => Promise<void>
  onDone: () => void
  onClose: () => void
}) {
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function confirm() {
    setBusy(true)
    setError(null)
    try {
      await onConfirm()
      onDone()
    } catch (e) {
      setError(e as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={title} onClose={onClose}>
      <p>{message}</p>
      {error && <p className="error" role="alert">{error.message}</p>}
      <div className="modal-actions">
        <button type="button" onClick={onClose}>{t('Cancel')}</button>
        <button className="danger" disabled={busy} onClick={confirm}>{action}</button>
      </div>
    </Modal>
  )
}
