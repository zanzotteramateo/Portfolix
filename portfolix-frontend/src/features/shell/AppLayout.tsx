import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { useAppDispatch } from '../../app/hooks'
import { Logo } from '../../components/Logo'
import { CloseIcon, HistoryIcon, LayersIcon, MenuIcon } from '../../components/icons'
import { AccountControls } from '../account/AccountControls'
import { fetchAssets } from '../assets/assetsSlice'
import { PortfolioNav } from '../portfolios/PortfolioNav'
import { fetchPortfolios } from '../portfolios/portfoliosSlice'
import { fetchPreferences } from '../preferences/preferencesSlice'
import styles from './AppLayout.module.css'

/**
 * El esqueleto de la app con sesión: menú lateral (con "Mis portafolios" y la cuenta abajo) y área de contenido.
 * Al entrar pide lo que comparten todas las pantallas. En celular el menú lateral es un cajón (debajo de 860px,
 * ver AppLayout.module.css): la barra superior con el botón de hamburguesa solo se ve en ese ancho.
 */
export function AppLayout() {
  const dispatch = useAppDispatch()
  const location = useLocation()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [drawerPathname, setDrawerPathname] = useState(location.pathname)

  // Cerrar el cajón al navegar a otra pantalla (un link del menú, crear/duplicar un portafolio).
  if (location.pathname !== drawerPathname) {
    setDrawerPathname(location.pathname)
    setDrawerOpen(false)
  }

  useEffect(() => {
    // Cada thunk tiene un condition: si ya se pidió, no vuelve a salir (ej.: el modo estricto de React en dev).
    dispatch(fetchPreferences())
    dispatch(fetchPortfolios())
    dispatch(fetchAssets())
  }, [dispatch])

  useEffect(() => {
    if (!drawerOpen) return
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') setDrawerOpen(false)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [drawerOpen])

  return (
    <div className={styles.layout}>
      <header className={styles.topbar}>
        <button
          type="button"
          className={styles.menuButton}
          onClick={() => setDrawerOpen((open) => !open)}
          aria-label={drawerOpen ? 'Cerrar menú' : 'Abrir menú'}
          aria-expanded={drawerOpen}
        >
          {drawerOpen ? <CloseIcon /> : <MenuIcon />}
        </button>
        <Logo size="small" />
      </header>

      {drawerOpen && <div className={styles.overlay} onClick={() => setDrawerOpen(false)} />}

      <aside className={`${styles.sidebar} ${drawerOpen ? styles.sidebarOpen : ''}`}>
        <div className={styles.brand}>
          <Logo size="small" />
        </div>
        <nav className={styles.nav} aria-label="Principal">
          <NavLink to="/transactions" className={({ isActive }) => `${styles.navItem} ${isActive ? styles.active : ''}`}>
            <HistoryIcon /> Historial de Transacciones
          </NavLink>
          <NavLink to="/" end className={({ isActive }) => `${styles.navItem} ${isActive ? styles.active : ''}`}>
            <LayersIcon /> Todos los Portafolios
          </NavLink>
        </nav>
        <PortfolioNav />

        <AccountControls />
      </aside>

      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  )
}
