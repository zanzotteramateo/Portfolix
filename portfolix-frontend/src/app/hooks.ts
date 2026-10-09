import { useDispatch, useSelector } from 'react-redux'
import type { AppDispatch, RootState } from './store'

/** useDispatch y useSelector con los tipos de esta app: usar siempre estos. */
export const useAppDispatch = useDispatch.withTypes<AppDispatch>()
export const useAppSelector = useSelector.withTypes<RootState>()
