import { useSelector } from 'react-redux'

export default function OverviewManager() {
  const user = useSelector((state) => state.user.user)
}
