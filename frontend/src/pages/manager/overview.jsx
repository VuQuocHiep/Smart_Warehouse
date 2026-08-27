import { useSelector } from 'react-redux'

export default function OverviewManager() {
  const user = useSelector((state) => state.user.user)

  return (
    <main className="rolePage managerRole">
      <section>
        <p>SMART WAREHOUSE</p>
        <h1>Giao diện Manager</h1>
        <span>{user?.email}</span>
      </section>
    </main>
  )
}
