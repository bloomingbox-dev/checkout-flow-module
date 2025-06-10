export type CheckoutModuleEvents = {
  onSuccess: (params: { paymentId: string }) => void;
  onFail: (params: { error: string }) => void;
};

export type EnvironmentConfig = {
  publicKey: string;
  environment: 'sandbox' | 'production';
};

export type InitializeCheckoutPayload = {
  id: string;
  payment_session_token: string;
  payment_session_secret: string;
  _links: {
    self: {
      href: string;
    };
  };
};
