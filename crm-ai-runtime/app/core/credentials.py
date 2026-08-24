class RuntimeCredentialStore:
    def __init__(self):
        self._authorizations: dict[str, str] = {}

    def register(self, key: str | None, authorization: str | None) -> None:
        if key and authorization:
            self._authorizations[str(key)] = authorization

    def authorization(self, key: str | None) -> str | None:
        if not key:
            return None
        return self._authorizations.get(str(key))

    def revoke(self, key: str | None) -> None:
        if key:
            self._authorizations.pop(str(key), None)


runtime_credential_store = RuntimeCredentialStore()
