import { FoIconName } from '../../../shared/ui/icon/icon.component';

export interface CategoryIconOption {
  name: FoIconName;
  label: string;
}

export interface CategoryColorOption {
  hex: string;
  label: string;
}

export const CATEGORY_ICON_OPTIONS: readonly CategoryIconOption[] = [
  { name: 'shopping-cart', label: 'Supermercado' },
  { name: 'house', label: 'Hogar' },
  { name: 'plug-zap', label: 'Servicios' },
  { name: 'heart-pulse', label: 'Salud' },
  { name: 'graduation-cap', label: 'Educación' },
  { name: 'car', label: 'Auto' },
  { name: 'fuel', label: 'Combustible' },
  { name: 'wrench', label: 'Mantenimiento' },
  { name: 'bus', label: 'Transporte' },
  { name: 'party-popper', label: 'Ocio' },
  { name: 'shirt', label: 'Indumentaria' },
  { name: 'sofa', label: 'Muebles' },
  { name: 'paw-print', label: 'Mascotas' },
  { name: 'baby', label: 'Familia' },
  { name: 'laptop', label: 'Tecnología' },
  { name: 'repeat', label: 'Suscripciones' },
  { name: 'gift', label: 'Regalos' },
  { name: 'briefcase', label: 'Trabajo' },
  { name: 'trending-up', label: 'Inversiones' },
  { name: 'piggy-bank', label: 'Ahorro' },
];

export const CATEGORY_COLOR_OPTIONS: readonly CategoryColorOption[] = [
  { hex: '#0F766E', label: 'Verde petróleo' },
  { hex: '#2563EB', label: 'Azul' },
  { hex: '#7C3AED', label: 'Violeta' },
  { hex: '#DB2777', label: 'Rosa' },
  { hex: '#DC2626', label: 'Rojo' },
  { hex: '#EA580C', label: 'Naranja' },
  { hex: '#D97706', label: 'Ámbar' },
  { hex: '#16A34A', label: 'Verde' },
  { hex: '#0891B2', label: 'Cian' },
  { hex: '#4F46E5', label: 'Índigo' },
  { hex: '#65A30D', label: 'Lima' },
  { hex: '#52525B', label: 'Gris' },
];

export const DEFAULT_CATEGORY_ICON: FoIconName = 'shopping-cart';
export const DEFAULT_CATEGORY_COLOR = '#0F766E';
