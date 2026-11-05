import { useState } from 'react'
import { ApiError } from '../api/client'
import { Modal } from './Modal'

export function ConfirmDialog({ title, message, onConfirm, onDone, onClose }: {
  title: string
  message: string
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
        <button type="button" onClick={onClose}>Cancel</button>
        <button className="danger" disabled={busy} onClick={confirm}>Delete</button>
      </div>
    </Modal>
  )
}
