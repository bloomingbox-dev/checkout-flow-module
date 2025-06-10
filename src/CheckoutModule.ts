import { NativeModule, requireNativeModule } from 'expo';

import {
  CheckoutModuleEvents,
  EnvironmentConfig,
  InitializeCheckoutPayload,
} from './CheckoutModule.types';

declare class CheckoutModule extends NativeModule<CheckoutModuleEvents> {
  setCredentials(config: EnvironmentConfig): Promise<void>;
  initializeCheckout(session: InitializeCheckoutPayload): Promise<void>;
  renderFlow(): Promise<void>;
}

export default requireNativeModule<CheckoutModule>('CheckoutModule');
