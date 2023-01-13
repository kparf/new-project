module.exports = {
  parser: "@typescript-eslint/parser",
  extends: [
    "plugin:jsx-a11y/recommended",
    "plugin:styled-components-a11y/recommended",
    "plugin:react-hooks/recommended",
  ],
  rules: {
    "react/no-multi-comp": ["error", { ignoreStateless: false }],
    "prettier/prettier": "error",
    "@typescript-eslint/consistent-type-imports": "error",
    "import/no-default-export": "error",
    "unused-imports/no-unused-imports-ts": "error",
    "arrow-body-style": "error",
    quotes: ["error", "double", { avoidEscape: true }],
    "no-fallthrough": "error",
    "react/jsx-key": [2, { checkFragmentShorthand: true }],
    "no-unused-vars": [
      "error",
      {
        args: "after-used",
        argsIgnorePattern: "^_",
        ignoreRestSiblings: true,
      },
    ],
    "no-restricted-imports": ["error"],
    "jsx-a11y/label-has-associated-control": [
      "error",
      {
        labelComponents: ["Label"],
        controlComponents: ["Input", "CheckBox"],
      },
    ],
    "jsx-a11y/label-has-for": "off", //deprecated rule, active because of styled-components-a11y 0.34 version
    "no-use-before-define": "off",
    "@typescript-eslint/no-use-before-define": [1, { variables: false }],
  },
  overrides: [
    {
      files: ["**/stories.tsx"],
      rules: {
        "import/no-default-export": "off",
      },
    },
  ],
  plugins: [
    "jsx-a11y",
    "prettier",
    "unused-imports",
    "@typescript-eslint",
    "eslint-plugin-import",
    "eslint-plugin-react",
    "eslint-plugin-react-hooks",
  ],
  settings: {
    react: {
      version: "detect",
    },
  },
}
