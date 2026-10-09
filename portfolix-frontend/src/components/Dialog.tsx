import { useEffect, useId, useRef, type ReactNode } from 'react'
import { CloseIcon } from './icons'
import styles from './Dialog.module.css'

/**
 * Modal con el <dialog> nativo: el navegador ya resuelve el foco atrapado adentro, la tecla Esc y el fondo
 * oscuro (::backdrop). Se abre al montarse; el que lo usa lo desmonta en onClose.
 * El cuerpo y los botones van como hijos (DialogBody y DialogFooter), así un <form> puede envolver a los dos.
 */
export function Dialog({ title, subtitle, onClose, width = 500, children }: {
  title: string
  subtitle?: ReactNode
  onClose: () => void
  width?: number
  children: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) {
      dialog.showModal()
    }
  }, [])

  return (
    <dialog
      ref={ref}
      className={styles.dialog}
      style={{ width }}
      aria-labelledby={titleId}
      onClose={onClose} // Esc, el botón de cerrar o dialog.close()
    >
      <header className={styles.header}>
        <div>
          <h2 id={titleId} className={styles.title}>
            {title}
          </h2>
          {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
        </div>
        <button type="button" className={styles.close} onClick={() => ref.current?.close()} aria-label="Cerrar">
          <CloseIcon />
        </button>
      </header>
      {children}
    </dialog>
  )
}

export function DialogBody({ children }: { children: ReactNode }) {
  return <div className={styles.body}>{children}</div>
}

export function DialogFooter({ children }: { children: ReactNode }) {
  return <footer className={styles.footer}>{children}</footer>
}
