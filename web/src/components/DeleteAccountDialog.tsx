import { useState } from 'react'
import { ApiError, api } from '../api/client'
import type { Account } from '../api/client'
import { Modal } from './Modal'

export function DeleteAccountDialog({ account, onDeleted, onClose }: {
  account: Account
  onDeleted: () => void
  onClose: () => void
}) {
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  async function confirm() {
    setBusy(true)
    setError(null)
    try {
      await api.deleteAccount(account.id)
      onDeleted()
    } catch (e) {
      setError(e as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Delete account?" onClose={onClose}>
      <p>“{account.name}” will be permanently deleted.</p>
      {error && <p className="error" role="alert">{error.message}</p>}
      <div className="modal-actions">
        <button type="button" onClick={onClose}>Cancel</button>
        <button className="danger" disabled={busy} onClick={confirm}>Delete</button>
      </div>
    </Modal>
  )
}
