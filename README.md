# React Template
## Local development:
Node.js: latest LTS. 

Just install packages using `yarn` command

## Useful commands:

`yarn start` - starts the application (http://localhost:3000/). By default, it starts on the 3000 port, but you can override
it providing PORT env variable, for example: `PORT=9313 yarn start`.

`yarn build` - builds static javascript and css files.

`yarn test` - executes all unit-tests. Test-files should have .ts/.tsx mask and the following name format _filename.spec.ts_ or _filename.test.ts_

### Different code checks that can be useful on CI
1. `yarn check:types` - TypeScript
2. `yarn lint-check` - ESLint (code style)
3. `yarn prettier-check:ts` - prettier (formatting ts/tsx)
4. `yarn prettier-check:styles` - prettier (formatting styles)

#### Code style fixes: 
1. `yarn lint-fix` - fix code style issues
2. `yarn prettier-fix` and `yarn prettier-fix:styles` - fix formatting

### Useful info:
- `.scss` is already added
- `.svg` support is already added