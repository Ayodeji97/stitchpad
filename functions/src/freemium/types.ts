export interface CustomerSlotInfo {
  id: string;
  /** ms-since-epoch of last activity (order, message, update). 0 if never. */
  lastActivityMs: number;
  slotState: 'active' | 'locked';
}

export interface SlotChange {
  id: string;
  toState: 'active' | 'locked';
}

export interface ReconcileSlotsResponse {
  changes: SlotChange[];
  totalActiveAfter: number;
  totalLockedAfter: number;
}
