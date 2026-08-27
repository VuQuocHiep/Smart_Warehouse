import { useEffect } from 'react'
import { useDispatch } from 'react-redux'

import { clearUser, setUser } from '../redux/userSlice.js'
import { decodeJwt, getRolesFromToken, getStoredAuth } from '../utils/auth.js'

export default function AuthBootstrap({ children }) {
  const dispatch = useDispatch()

  useEffect(() => {
    const auth = getStoredAuth()

    if (!auth?.token) {
      dispatch(clearUser())
      return
    }

    const payload = decodeJwt(auth.token)
    dispatch(
      setUser({
        email: payload?.sub || '',
        role: auth.role,
        roles: getRolesFromToken(auth.token),
        token: auth.token,
      })
    )
  }, [dispatch])

  return children
}
