import { useSelector } from 'react-redux'

export default function OverviewAdmin() {
  const user = useSelector((state) => state.user.user)

  return (
    <main className="rolePage adminRole">
      <section>
        <p>SMART WAREHOUSE</p>
        <h1>Giao diện Admin</h1>
        <span>{user?.email}</span>
      </section>
    </main>
  )
}
