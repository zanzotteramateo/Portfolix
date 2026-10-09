/*
 * Al terminar la corrida se vacía Mailpit: los mails de las cuentas de prueba (ya borradas) no se quedan en la
 * bandeja de dev. Corre aunque haya fallado algún test.
 */
export default async function globalTeardown(): Promise<void> {
  await fetch('http://localhost:8025/api/v1/messages', { method: 'DELETE' })
}
