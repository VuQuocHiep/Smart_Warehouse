import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { BrowserRouter } from 'react-router-dom'
import { App as AntdApp } from 'antd'

import App from './App.jsx'
import AuthBootstrap from './components/AuthBootstrap.jsx'
import { store } from './redux/store.js'
import './index.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <Provider store={store}>
      <BrowserRouter>
        <AntdApp>
          <AuthBootstrap>
            <App />
          </AuthBootstrap>
        </AntdApp>
      </BrowserRouter>
    </Provider>
  </StrictMode>
)
