declare module 'sockjs-client' {
  export default class SockJS extends EventTarget {
    constructor(url: string, options?: Record<string, unknown>)
    close(): void
  }
}

declare module 'stompjs' {
  export default class Stomp {
    static over(socket: any): Stomp
    connect(headers: Record<string, unknown>, connectCallback: () => void, errorCallback?: (err: any) => void): void
    disconnect(disconnectCallback?: () => void, headers?: Record<string, unknown>): void
    subscribe(destination: string, callback: (message: any) => void, headers?: Record<string, unknown>): { unsubscribe(): void }
  }
}
