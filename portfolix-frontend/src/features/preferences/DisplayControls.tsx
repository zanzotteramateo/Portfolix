import { useAppDispatch, useAppSelector } from '../../app/hooks'
import { SegmentedControl } from '../../components/SegmentedControl'
import { EyeIcon, EyeOffIcon } from '../../components/icons'
import { updatePreferences } from './preferencesSlice'
import styles from './DisplayControls.module.css'

/**
 * La moneda (ARS o USD) y el ojo de "ocultar montos" de la cabecera. Son preferencias del usuario: cambiarlas
 * en una pantalla las cambia en todas, y se guardan con PATCH /me/preferences.
 */
export function DisplayControls() {
  const dispatch = useAppDispatch()
  const { currency, hideAmounts } = useAppSelector((state) => state.preferences)

  return (
    <>
      <SegmentedControl
        label="Moneda"
        options={[
          { value: 'ARS', label: 'ARS' },
          { value: 'USD', label: 'USD' },
        ]}
        value={currency}
        onChange={(value) => dispatch(updatePreferences({ currency: value }))}
      />
      <button
        type="button"
        className={styles.iconButton}
        onClick={() => dispatch(updatePreferences({ hideAmounts: !hideAmounts }))}
        aria-pressed={hideAmounts}
        aria-label={hideAmounts ? 'Mostrar montos' : 'Ocultar montos'}
        title={hideAmounts ? 'Mostrar montos' : 'Ocultar montos'}
      >
        {hideAmounts ? <EyeOffIcon /> : <EyeIcon />}
      </button>
    </>
  )
}
