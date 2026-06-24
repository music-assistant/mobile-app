# Contributing

## Contributing code

The project is in an early stage of development. Any help (especially from designers and iOS developers) is appreciated. To contribute:

1. [Set up your development environment](DEV-ENVIRONMENT.md)
2. [Find an issue](https://github.com/music-assistant/mobile-app/issues) to work on - if you've noticed something wrong or missing, please file an issue about it before creating a PR so it can be discussed
3. Ask in the issue if you can work on it - this prevents multiple people from working on an issue at the same time
4. Submit a PR with "Closes #<issue number>" at the top of the description

## Git workflow

- PRs should be merged to `main`
- When a release is made, the commit used should be tagged with the release version using the format `android-<version name>` and `ios-<version name>` for Android and iOS releases respectively.
- If a hotfix needs to be made to a previous stable release, a new branch should be created from the stable release tag and PRs should be merged to that. Once the hotfix is released, these changes will need to be merged to `main`.