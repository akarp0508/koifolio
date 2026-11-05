import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const dialog = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    dialog.current?.showModal()
  }, [])

  return (
    <dialog ref={dialog} className="modal" onClose={onClose}>
      <h2>{title}</h2>
      {children}
    </dialog>
  )
}
