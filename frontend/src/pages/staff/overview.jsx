import { useSelector } from 'react-redux'

export default function OverviewStaff() {
  const user = useSelector((state) => state.user.user)

  return (
    <main className="rolePage staffRole">
      <section>
        <p>SMART WAREHOUSE</p>
        <h1>Giao diện Staff</h1>
        <span>{user?.email}</span>
      </section>
    </main>
  )
}
