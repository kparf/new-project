import { render, screen } from "@testing-library/react"
import App from "./App"

test("renders react template div", () => {
  render(<App />)
  const div = screen.getByTestId("react-template")
  expect(div).toBeInTheDocument()
})
