import { useEffect } from 'react'
import {
  BrowserRouter,
  Link,
  Route,
  Routes,
  useLocation,
} from 'react-router-dom'
import AppLayout from './components/AppLayout.tsx'
import GameLobbyPage from './pages/GameLobbyPage.tsx'
import GameTablePage from './pages/GameTablePage.tsx'
import HomePage from './pages/HomePage.tsx'

function RouteScroll() {
  const location = useLocation()

  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      if (location.hash) {
        document.getElementById(location.hash.slice(1))?.scrollIntoView()
        return
      }

      window.scrollTo({ top: 0 })
    })

    return () => cancelAnimationFrame(frame)
  }, [location.hash, location.pathname])

  return null
}

function NotFoundPage() {
  return (
    <section className="not-found-page">
      <span className="section-kicker">404</span>
      <h1>That table does not exist.</h1>
      <p>Return to the lobby and choose one of the available card games.</p>
      <Link className="primary-action" to="/#games">
        Back to games
      </Link>
    </section>
  )
}

function App() {
  return (
    <BrowserRouter>
      <RouteScroll />
      <Routes>
        <Route path="games/:gameId/lobbies/:lobbyId" element={<GameTablePage />} />
        <Route element={<AppLayout />}>
          <Route index element={<HomePage />} />
          <Route path="games/:gameId" element={<GameLobbyPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
