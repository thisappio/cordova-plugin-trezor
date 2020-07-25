export interface CordovaTrezor {
    getPublicKeys(request: PublicKeyRequest): Promise<PublicKeyResponse>;
    manifest(request: Manifest): void;
}

export interface PublicKeyRequest {
    bundle: { path: string }[];
}

export interface PublicKeyResponse {
    success: boolean;
    payload: PublicKey[] | ErrorPayload;
}

export interface ErrorPayload {
    error: string;
}

export interface PublicKey {
    path: number[];
    serializedPath: string;
    xpub: string;
    chainCode: string;
    childNum: number;
    publicKey: string;
    fingerprint: number;
    depth: number;
    xpubSegwit?: string;
}

export interface Manifest {
    email: string;
    appUrl: string;
}

declare var cordovaTrezor: CordovaTrezor;