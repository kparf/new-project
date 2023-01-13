import styles from "./app.styles.scss"

export type AppProps = {
  prop1?: string
}

function App({ prop1 }: AppProps) {
  return (
    <div className={styles.App} data-testid="react-template">
      React Template Example {prop1}
    </div>
  )
}

export default App
