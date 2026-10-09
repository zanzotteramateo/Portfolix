import { useId, useState } from 'react'
import type { DecimalSeparator, PreferencesUpdateRequest } from '../../api/types'
import { useAppDispatch, useAppSelector } from '../../app/hooks'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { Dialog, DialogBody, DialogFooter } from '../../components/Dialog'
import { formatWithCents } from '../../format/format'
import { updatePreferences } from '../preferences/preferencesSlice'
import styles from './AppSettingsDialog.module.css'

/**
 * "Ajustes de la aplicación" (el engranaje): el tema y el separador decimal. Son preferencias del usuario: se
 * aplican al instante y se guardan con PATCH /me/preferences. Si el backend las rechaza, vuelven atrás.
 */
export function AppSettingsDialog({ onClose }: { onClose: () => void }) {
  const dispatch = useAppDispatch()
  const { theme, decimalSeparator } = useAppSelector((state) => state.preferences)
  const [error, setError] = useState<string | null>(null)
  const separatorId = useId()

  async function save(changes: PreferencesUpdateRequest) {
    setError(null)
    const result = await dispatch(updatePreferences(changes))
    if (updatePreferences.rejected.match(result)) {
      setError('No pudimos guardar el cambio. Probá de nuevo.')
    }
  }

  return (
    <Dialog title="Ajustes de la aplicación" onClose={onClose} width={440}>
      <DialogBody>
        {error && <Alert>{error}</Alert>}

        <section className={styles.section} aria-label="Tema">
          <h3 className={styles.sectionTitle}>Tema</h3>
          <div className={styles.row}>
            <div className={styles.rowText}>
              <span className={styles.rowLabel}>Modo oscuro</span>
              <span className={styles.rowHint}>Cambia los colores de toda la app</span>
            </div>
            <button
              type="button"
              role="switch"
              aria-checked={theme === 'DARK'}
              aria-label="Modo oscuro"
              className={`${styles.switch} ${theme === 'DARK' ? styles.switchOn : ''}`}
              onClick={() => save({ theme: theme === 'DARK' ? 'LIGHT' : 'DARK' })}
            >
              <span className={styles.knob} />
            </button>
          </div>
        </section>

        <section className={styles.section} aria-label="Preferencias de formato">
          <h3 className={styles.sectionTitle}>Preferencias de formato</h3>
          <div className={styles.row}>
            <label htmlFor={separatorId} className={styles.rowLabel}>
              Separador decimal
            </label>
            <select
              id={separatorId}
              className={styles.select}
              value={decimalSeparator}
              onChange={(e) => save({ decimalSeparator: e.target.value as DecimalSeparator })}
            >
              <option value="COMMA">Coma ( , )  ·  1.234,56</option>
              <option value="PERIOD">Punto ( . )  ·  1,234.56</option>
            </select>
          </div>
          <p className={styles.preview}>
            Vista previa: <strong>$ {formatWithCents(1234567.89, decimalSeparator)}</strong>
          </p>
        </section>
      </DialogBody>
      <DialogFooter>
        <Button type="button" onClick={onClose}>
          Listo
        </Button>
      </DialogFooter>
    </Dialog>
  )
}
