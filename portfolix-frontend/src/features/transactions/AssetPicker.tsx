import { useId, useMemo, useState, type KeyboardEvent } from 'react'
import type { AssetResponse } from '../../api/types'
import { AssetIcon, TypeBadge } from '../../components/AssetBadges'
import styles from './RegisterTransactionDialog.module.css'

const MAX_RESULTS = 8

/** Minúsculas y sin tildes, como la búsqueda del backend: "energia" encuentra "Pampa Energía". */
function normalize(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()
}

/**
 * Buscador de activos del catálogo (combobox accesible): se escribe el símbolo o el nombre, se elige con
 * el mouse o con las flechas y Enter. Esc cierra la lista sin cerrar el modal.
 */
export function AssetPicker({ assets, value, onChange, invalid, describedBy }: {
  assets: AssetResponse[]
  /** El símbolo elegido ('' = ninguno). */
  value: string
  onChange: (symbol: string) => void
  invalid: boolean
  describedBy?: string
}) {
  const listId = useId()
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const selected = assets.find((a) => a.symbol === value)

  const results = useMemo(() => {
    const q = normalize(query.trim())
    return assets.filter((a) => !q || normalize(a.symbol).includes(q) || normalize(a.name).includes(q)).slice(0, MAX_RESULTS)
  }, [assets, query])

  function choose(asset: AssetResponse) {
    onChange(asset.symbol)
    setQuery('')
    setOpen(false)
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      setOpen(true)
      const step = event.key === 'ArrowDown' ? 1 : -1
      setActive((i) => (i + step + results.length) % Math.max(results.length, 1))
    } else if (event.key === 'Enter' && open && results[active]) {
      event.preventDefault() // no enviar el formulario
      choose(results[active])
    } else if (event.key === 'Escape' && open) {
      event.preventDefault() // cierra la lista, no el modal
      event.stopPropagation()
      setOpen(false)
    }
  }

  return (
    <div className={styles.picker}>
      {selected && !open && <AssetIcon symbol={selected.symbol} />}
      <input
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={open && results[active] ? `${listId}-${active}` : undefined}
        aria-invalid={invalid || undefined}
        aria-describedby={describedBy}
        aria-label="Activo"
        className={`${styles.pickerInput} ${invalid ? styles.invalid : ''}`}
        placeholder="Buscá por símbolo o nombre (ej.: BTC, YPF, Apple)"
        value={open ? query : selected ? `${selected.symbol} · ${selected.name}` : query}
        onChange={(e) => {
          setQuery(e.target.value)
          setActive(0)
          setOpen(true)
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={onKeyDown}
        autoComplete="off"
      />
      {open && (
        <ul id={listId} role="listbox" className={styles.options}>
          {results.length === 0 && <li className={styles.noOptions}>No hay activos con ese nombre</li>}
          {results.map((asset, index) => (
            <li
              key={asset.symbol}
              id={`${listId}-${index}`}
              role="option"
              aria-selected={index === active}
              className={`${styles.option} ${index === active ? styles.optionActive : ''}`}
              // mousedown en vez de click: así el input no pierde el foco (y la lista no se cierra) antes de elegir.
              onMouseDown={(e) => {
                e.preventDefault()
                choose(asset)
              }}
              onMouseEnter={() => setActive(index)}
            >
              <AssetIcon symbol={asset.symbol} />
              <span className={styles.optionSymbol}>{asset.symbol}</span>
              <span className={styles.optionName}>{asset.name}</span>
              <TypeBadge type={asset.type} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
