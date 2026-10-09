import { PlusIcon } from '../../components/icons'
import styles from './Dashboard.module.css'

const STEPS = [
  { title: 'Creá un portafolio', text: 'Agrupá tus inversiones por objetivo: jubilación, ahorro, trading.' },
  { title: 'Registrá una compra', text: 'Cargá el activo, la cantidad, el precio y la fecha de la operación.' },
  { title: 'Seguí tu rendimiento', text: 'Mirá ganancias, distribución y la tendencia de cada activo.' },
]

/** Sin posiciones: el estado vacío del diseño ("Tu cartera está vacía" y los primeros pasos). */
export function EmptyState({ portfolioName, onRegister, onCreatePortfolio }: {
  /** Si se está mirando un portafolio en particular. */
  portfolioName: string | null
  onRegister: () => void
  onCreatePortfolio: () => void
}) {
  return (
    <section className={styles.empty}>
      <h2 className={styles.emptyTitle}>{portfolioName ? 'Este portafolio está vacío' : 'Tu cartera está vacía'}</h2>
      <p className={styles.emptyText}>
        {portfolioName
          ? `Registrá una operación en “${portfolioName}” para empezar a seguirla.`
          : 'Registrá tu primera operación para empezar a seguir tus inversiones.'}
      </p>
      <div className={styles.emptyActions}>
        <button type="button" className={styles.primaryButton} onClick={onRegister}>
          <PlusIcon size={15} /> Registrar primera transacción
        </button>
        {!portfolioName && (
          <button type="button" className={styles.secondaryButton} onClick={onCreatePortfolio}>
            Crear un portafolio
          </button>
        )}
      </div>
      {!portfolioName && (
        <ol className={styles.steps}>
          {STEPS.map((step, index) => (
            <li key={step.title} className={styles.step}>
              <span className={styles.stepNumber}>{index + 1}</span>
              <strong>{step.title}</strong>
              <span>{step.text}</span>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
