import { useState } from 'react'
import { NavLink, useMatch, useNavigate } from 'react-router'
import type { ApiErrorData } from '../../api/client'
import type { PortfolioResponse } from '../../api/types'
import { useAppDispatch, useAppSelector } from '../../app/hooks'
import { Menu } from '../../components/Menu'
import { ChevronDownIcon, CopyIcon, MoreIcon, PencilIcon, PlusIcon, TrashIcon } from '../../components/icons'
import { DeletePortfolioDialog } from './DeletePortfolioDialog'
import { NameForm } from './NameForm'
import {
  createFormClosed,
  createFormOpened,
  createPortfolio,
  duplicatePortfolio,
  renamePortfolio,
} from './portfoliosSlice'
import styles from './PortfolioNav.module.css'

/** "Mis portafolios" del menú lateral: la lista, crear, renombrar, duplicar y eliminar. */
export function PortfolioNav() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const { items, status, createFormOpen } = useAppSelector((state) => state.portfolios)
  const [collapsed, setCollapsed] = useState(false)
  const [renamingId, setRenamingId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState<PortfolioResponse | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const current = useMatch('/portfolios/:portfolioId')?.params.portfolioId

  async function create(name: string): Promise<ApiErrorData | null> {
    const result = await dispatch(createPortfolio(name))
    if (createPortfolio.fulfilled.match(result)) {
      navigate(`/portfolios/${result.payload.id}`)
      return null
    }
    return result.payload ?? null
  }

  async function rename(id: number, name: string): Promise<ApiErrorData | null> {
    const result = await dispatch(renamePortfolio({ id, name }))
    if (renamePortfolio.fulfilled.match(result)) {
      setRenamingId(null)
      return null
    }
    return result.payload ?? null
  }

  async function duplicate(id: number) {
    setActionError(null)
    const result = await dispatch(duplicatePortfolio(id))
    if (duplicatePortfolio.fulfilled.match(result)) {
      navigate(`/portfolios/${result.payload.id}`)
    } else {
      setActionError(result.payload?.message ?? 'No se pudo duplicar el portafolio')
    }
  }

  return (
    <section className={styles.section} aria-label="Mis portafolios">
      <button
        type="button"
        className={styles.sectionHeader}
        onClick={() => setCollapsed((c) => !c)}
        aria-expanded={!collapsed}
      >
        Mis portafolios
        <ChevronDownIcon size={14} className={collapsed ? styles.chevronCollapsed : undefined} />
      </button>

      {!collapsed && (
        <ul className={styles.list}>
          {status === 'ready' && items.length === 0 && !createFormOpen && (
            <li className={styles.empty}>Todavía no creaste ninguno</li>
          )}
          {items.map((portfolio) =>
            renamingId === portfolio.id ? (
              <li key={portfolio.id}>
                <NameForm
                  label="Nuevo nombre del portafolio"
                  initialName={portfolio.name}
                  onSubmit={(name) => rename(portfolio.id, name)}
                  onCancel={() => setRenamingId(null)}
                />
              </li>
            ) : (
              <li key={portfolio.id} className={styles.item}>
                <NavLink
                  to={`/portfolios/${portfolio.id}`}
                  className={({ isActive }) => `${styles.link} ${isActive ? styles.active : ''}`}
                >
                  <span className={styles.dot} aria-hidden="true" />
                  <span className={styles.name}>{portfolio.name}</span>
                </NavLink>
                <Menu
                  label={`Opciones de ${portfolio.name}`}
                  className={`${styles.more} ${String(portfolio.id) === current ? styles.moreVisible : ''}`}
                  trigger={<MoreIcon size={14} />}
                  items={[
                    { label: 'Renombrar', icon: <PencilIcon size={14} />, onSelect: () => setRenamingId(portfolio.id) },
                    { label: 'Duplicar como nuevo', icon: <CopyIcon size={14} />, onSelect: () => duplicate(portfolio.id) },
                    {
                      label: 'Eliminar portafolio',
                      icon: <TrashIcon size={14} />,
                      danger: true,
                      onSelect: () => setDeleting(portfolio),
                    },
                  ]}
                />
              </li>
            ),
          )}
          {createFormOpen && (
            <li>
              <NameForm
                label="Nombre del portafolio nuevo"
                onSubmit={create}
                onCancel={() => dispatch(createFormClosed())}
              />
            </li>
          )}
        </ul>
      )}
      {actionError && (
        <p className={styles.actionError} role="alert">
          {actionError}
        </p>
      )}

      {!createFormOpen && (
        <button type="button" className={styles.createButton} onClick={() => dispatch(createFormOpened())}>
          <PlusIcon size={14} /> Crear Portafolio
        </button>
      )}

      {deleting && <DeletePortfolioDialog portfolio={deleting} onClose={() => setDeleting(null)} />}
    </section>
  )
}
