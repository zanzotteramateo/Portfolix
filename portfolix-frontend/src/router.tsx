import { createBrowserRouter, Navigate } from 'react-router'
import { PublicOnly, RequireAuth } from './features/auth/guards'
import { CheckEmailPage } from './features/auth/pages/CheckEmailPage'
import { ConfirmEmailChangePage } from './features/auth/pages/ConfirmEmailChangePage'
import { ForgotPasswordPage } from './features/auth/pages/ForgotPasswordPage'
import { LoginPage } from './features/auth/pages/LoginPage'
import { RegisterPage } from './features/auth/pages/RegisterPage'
import { ResetPasswordPage } from './features/auth/pages/ResetPasswordPage'
import { VerifyEmailPage } from './features/auth/pages/VerifyEmailPage'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { AppLayout } from './features/shell/AppLayout'
import { TransactionsPage } from './features/transactions/TransactionsPage'

/*
 * Rutas de la app. Las de los links de los mails (/verify-email, /reset-password, /confirm-email-change)
 * tienen que coincidir con las que arma el backend (portfolix.mail.frontend-url + la ruta).
 */
export const router = createBrowserRouter([
  {
    element: <PublicOnly />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/forgot-password', element: <ForgotPasswordPage /> },
    ],
  },
  { path: '/check-email', element: <CheckEmailPage /> },
  { path: '/verify-email', element: <VerifyEmailPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  { path: '/confirm-email-change', element: <ConfirmEmailChangePage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: <DashboardPage /> },
          { path: '/portfolios/:portfolioId', element: <DashboardPage /> },
          { path: '/transactions', element: <TransactionsPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])
