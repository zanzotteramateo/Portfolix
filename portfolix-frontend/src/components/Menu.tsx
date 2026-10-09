import { useId, useRef, type KeyboardEvent, type ReactNode } from 'react'
import styles from './Menu.module.css'

export interface MenuItem {
  label: string
  icon?: ReactNode
  onSelect: () => void
  danger?: boolean
}

/**
 * Menú desplegable con el atributo popover del navegador: ya se cierra solo al hacer clic afuera o con Esc,
 * y se dibuja por encima de todo. Acá solo se lo ubica junto al botón y se navega con las flechas.
 */
export function Menu({ label, trigger, items, className }: {
  /** Para lectores de pantalla (ej.: "Opciones de Jubilación"). */
  label: string
  trigger: ReactNode
  items: MenuItem[]
  className?: string
}) {
  const id = useId()
  const buttonRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  function onToggle(event: { newState: string }) {
    const button = buttonRef.current
    const menu = menuRef.current
    if (event.newState !== 'open' || !button || !menu) {
      return
    }
    const rect = button.getBoundingClientRect()
    // Debajo del botón; si no entra en la pantalla (un menú del pie del menú lateral), encima.
    const fitsBelow = rect.bottom + menu.offsetHeight + 8 <= window.innerHeight
    menu.style.top = `${fitsBelow ? rect.bottom + 4 : rect.top - menu.offsetHeight - 4}px`
    menu.style.left = `${Math.min(rect.left, window.innerWidth - menu.offsetWidth - 8)}px`
    menu.querySelector<HTMLButtonElement>('[role="menuitem"]')?.focus()
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') {
      return
    }
    event.preventDefault()
    const options = [...(menuRef.current?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]') ?? [])]
    const current = options.indexOf(document.activeElement as HTMLButtonElement)
    const next = event.key === 'ArrowDown' ? current + 1 : current - 1
    options[(next + options.length) % options.length]?.focus()
  }

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        className={className}
        popoverTarget={id}
        aria-haspopup="menu"
        aria-label={label}
      >
        {trigger}
      </button>
      <div ref={menuRef} id={id} popover="auto" role="menu" className={styles.menu} onToggle={onToggle} onKeyDown={onKeyDown}>
        {items.map((item) => (
          <button
            key={item.label}
            type="button"
            role="menuitem"
            className={`${styles.item} ${item.danger ? styles.danger : ''}`}
            onClick={() => {
              menuRef.current?.hidePopover()
              item.onSelect()
            }}
          >
            {item.icon}
            {item.label}
          </button>
        ))}
      </div>
    </>
  )
}
